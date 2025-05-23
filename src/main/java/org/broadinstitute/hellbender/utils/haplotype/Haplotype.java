package org.broadinstitute.hellbender.utils.haplotype;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.util.Locatable;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.SimpleAllele;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.hellbender.utils.SimpleInterval;
import org.broadinstitute.hellbender.utils.Utils;
import org.broadinstitute.hellbender.utils.read.AlignmentUtils;
import org.broadinstitute.hellbender.utils.read.CigarBuilder;
import org.broadinstitute.hellbender.utils.read.ReadUtils;

import java.util.Arrays;
import java.util.Comparator;

public class Haplotype extends SimpleAllele implements Locatable{
    private static final long serialVersionUID = 1L;

    /**
     * Compares two haplotypes first by their lengths and then by lexicographic order of their bases.
     */
    public static final Comparator<Haplotype> SIZE_AND_BASE_ORDER =
            Comparator.comparingInt((Haplotype hap) -> hap.getBases().length)
                      .thenComparing(Allele::getBaseString);

    private Locatable genomeLocation = null;
    private EventMap eventMap = null;
    private Cigar cigar;
    private int alignmentStartHapwrtRef; //NOTE: this is the offset to a supposed array of reference bases held in memory and has nothing to do with start positions
    private int weakness = Integer.MIN_VALUE;
    private double score = Double.NaN;

    /**
     * see {@link org.broadinstitute.hellbender.tools.walkers.haplotypecaller.LongHomopolymerHaplotypeCollapsingEngine} for a description of the semantics of collapsing
     */
    private boolean isCollapsed;
    private int uniquenessValue;   // uniquely diffrentiates the haplotype from others with same ref/bases.

    // debug information for tracking kmer sizes used in graph construction for debug output
    private int kmerSize = 0;

    /**
     * Main constructor
     *
     * @param bases a non-null array of bases
     * @param isRef is this the reference haplotype?
     */
    public Haplotype( final byte[] bases, final boolean isRef ) {
        super(Arrays.copyOf(bases, bases.length), isRef);
    }

    /**
     * Create a new non-ref haplotype
     *
     * @param bases a non-null array of bases
     */
    public Haplotype( final byte[] bases ) {
        this(bases, false);
    }

    /**
     * Create a new haplotype with bases
     *
     * Requires bases.length == cigar.getReadLength()
     *
     * @param bases a non-null array of bases
     * @param isRef is this the reference haplotype?
     * @param alignmentStartHapwrtRef offset of this haplotype w.r.t. the reference (NOTE: this is NOT the aligned start, but an offset to a hypothetical reference array)
     * @param cigar the cigar that maps this haplotype to the reference sequence
     */
    public Haplotype( final byte[] bases, final boolean isRef, final int alignmentStartHapwrtRef, final Cigar cigar) {
        this(bases, isRef);
        this.alignmentStartHapwrtRef = alignmentStartHapwrtRef;
        setCigar(cigar);
    }

    public Haplotype( final byte[] bases, final boolean isRef, final Locatable loc, final Cigar cigar ) {
        this(bases, isRef);
        this.cigar = cigar;
        this.genomeLocation = loc;
    }


    public Haplotype( final byte[] bases, final Locatable loc ) {
        this(bases, false);
        this.genomeLocation = loc;
    }

    /**
     * Create a new Haplotype derived from this one that exactly spans the provided location
     *
     * Note that this haplotype must have a contain a genome loc for this operation to be successful.  If no
     * GenomeLoc is contained than @throws an IllegalStateException
     *
     * Also loc must be fully contained within this Haplotype's genomeLoc.  If not an IllegalArgumentException is
     * thrown.
     *
     * @param loc a location completely contained within this Haplotype's location
     * @return a new Haplotype within only the bases spanning the provided location, or null for some reason the haplotype would be malformed if
     */
    public Haplotype trim(final Locatable loc) {
        return trim(loc, false);
    }

    /**
     * Create a new Haplotype derived from this one that exactly spans the provided location
     *
     * Note that this haplotype must have a contain a genome loc for this operation to be successful.  If no
     * GenomeLoc is contained than @throws an IllegalStateException
     *
     * Also loc must be fully contained within this Haplotype's genomeLoc.  If not an IllegalArgumentException is
     * thrown.
     *
     * @param loc a location completely contained within this Haplotype's location
     * @param ignoreRefState should the reference state of the original Haplotype be ignored
     * @return a new Haplotype within only the bases spanning the provided location, or null for some reason the haplotype would be malformed if
     */
    public Haplotype trim(final Locatable loc, boolean ignoreRefState) {
        Utils.nonNull( loc, "Loc cannot be null");
        Utils.nonNull(genomeLocation, "Cannot trim a Haplotype without containing GenomeLoc");
        Utils.validateArg(new SimpleInterval(genomeLocation).contains(loc), () -> "Can only trim a Haplotype to a containing span.  My loc is " + genomeLocation + " but wanted trim to " + loc);
        Utils.nonNull( getCigar(), "Cannot trim haplotype without a cigar " + this);

        final int newStart = loc.getStart() - this.genomeLocation.getStart();
        final int newStop = newStart + loc.getEnd() - loc.getStart();

        // note: the following returns null if the bases covering the ref interval start or end in a deletion.
        final byte[] newBases = AlignmentUtils.getBasesCoveringRefInterval(newStart, newStop, getBases(), 0, getCigar());

        if ( newBases == null || newBases.length == 0 ) { // we cannot meaningfully chop down the haplotype, so return null
            return null;
        }

        // note: trimCigarByReference does not remove leading or trailing indels, while getBasesCoveringRefInterval does remove bases
        // of leading and trailing insertions.  We must remove leading and trailing insertions from the Cigar manually.
        // we keep leading and trailing deletions because these are necessary for haplotypes to maintain consistent reference coordinates
        final Cigar newCigar = AlignmentUtils.trimCigarByReference(getCigar(), newStart, newStop).getCigar();
        final boolean leadingInsertion = !newCigar.getFirstCigarElement().getOperator().consumesReferenceBases();
        final boolean trailingInsertion = !newCigar.getLastCigarElement().getOperator().consumesReferenceBases();
        final int firstIndexToKeepInclusive = leadingInsertion ? 1 : 0;
        final int lastIndexToKeepExclusive = newCigar.numCigarElements() - (trailingInsertion ? 1 : 0);

        if (lastIndexToKeepExclusive <= firstIndexToKeepInclusive) {    // edge case of entire cigar is insertion
            return null;
        }

        final Cigar leadingIndelTrimmedNewCigar = !(leadingInsertion || trailingInsertion)  ? newCigar :
                new CigarBuilder(false).addAll(newCigar.getCigarElements().subList(firstIndexToKeepInclusive, lastIndexToKeepExclusive)).make();

        final Haplotype ret = new Haplotype(newBases, ignoreRefState ? false : isReference());
        ret.setCigar(leadingIndelTrimmedNewCigar);
        ret.setGenomeLocation(loc);
        ret.setWeakness(weakness);
        ret.setScore(score);
        ret.setKmerSize(kmerSize);
        ret.setAlignmentStartHapwrtRef(newStart + getAlignmentStartHapwrtRef());
        return ret;
    }


    @Override
    public boolean equals( final Object h ) {
        return h instanceof Haplotype
                && getUniquenessValue() == ((Haplotype) h).getUniquenessValue()
                && isReference() == ((Haplotype) h).isReference()
                && Arrays.equals(getBases(), ((Haplotype) h).getBases());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getBases());
    }

    public EventMap getEventMap() {
        return eventMap;
    }

    public void setEventMap( final EventMap eventMap ) {
        this.eventMap = eventMap;
    }

    @Override
    public String toString() {
        return getDisplayString();
    }

    public void setGenomeLocation(final Locatable genomeLocation) {
        this.genomeLocation = genomeLocation;
    }

    @Override
    public String getContig() {
        return genomeLocation.getContig();
    }

    @Override
    public int getStart() {
        return genomeLocation.getStart();
    }

    @Override
    public int getEnd() {
        return genomeLocation.getEnd();
    }

    public int getAlignmentStartHapwrtRef() {
        return alignmentStartHapwrtRef;
    }

    public void setAlignmentStartHapwrtRef( final int alignmentStartHapwrtRef ) {
        this.alignmentStartHapwrtRef = alignmentStartHapwrtRef;
    }

    /**
     * Get the cigar for this haplotype.  Note that the cigar is guaranteed to be consolidated
     * in that multiple adjacent equal operates will have been merged
     * @return the cigar of this haplotype
     */
    public Cigar getCigar() {
        return cigar;
    }

    /**
     * Get the haplotype cigar extended by padSize M at the tail, consolidated into a clean cigar
     *
     * @param padSize how many additional Ms should be appended to the end of this cigar.  Must be >= 0
     * @return a newly allocated Cigar that consolidate(getCigar + padSize + M)
     */
    public Cigar getConsolidatedPaddedCigar(final int padSize) {
        Utils.validateArg( padSize >= 0, () -> "padSize must be >= 0 but got " + padSize);
        return new CigarBuilder().addAll(getCigar()).add(new CigarElement(padSize, CigarOperator.M)).make();
    }

    /**
     * Set the cigar of this haplotype to cigar.
     *
     * This method consolidates the cigar, so that 1M1M1I1M1M => 2M1I2M.  It does not remove leading or trailing deletions
     * because haplotypes, unlike reads, are pegged to a specific reference start and end.
     *
     * @param cigar a cigar whose readLength == length()
     */
    public void setCigar( final Cigar cigar ) {
        this.cigar = new CigarBuilder(false).addAll(cigar).make();
        Utils.validateArg( this.cigar.getReadLength() == length(), () -> "Read length " + length() + " not equal to the read length of the cigar " + cigar.getReadLength() + " " + this.cigar);
    }

    /**
     *
     * @param refAllele             allele, contained in this haplotype, to be replaced
     * @param altAllele             new allele, the bases of which replace those of refAllele
     * @param insertLocation     location in the genome at which the new allele starts
     *
     * Example: suppose this haplotype starts at position 100 on its contig and has bases ACCGTTATATCG and we wish to
     * delete the GTT.  Then refAllele = CGTT, alt allele = C, and insertLocation = 102.
     */
    public Haplotype insertAllele(final Allele refAllele, final Allele altAllele, final int insertLocation) {
        //special case for zeroth base deletion. In this case the common base is "outside" of the contig
        final byte[] myBases = getBases();
        if ((refAllele.length()>altAllele.length()) && (insertLocation==getStart()-1)){
            int delSize = refAllele.length() - altAllele.length();
            if (delSize > myBases.length){
                return null;
            }
            else{
                byte[] newHaplotypeBases = {};
                newHaplotypeBases = ArrayUtils.subarray(myBases, delSize, myBases.length); // bases before the variant
                return new Haplotype(newHaplotypeBases);
            }
        }


        final Pair<Integer, CigarOperator> haplotypeInsertLocationAndOperator = ReadUtils.getReadIndexForReferenceCoordinate(getStart(), cigar, insertLocation);

        // can't insert outside the haplotype or into a deletion
        if( haplotypeInsertLocationAndOperator.getLeft() == ReadUtils.READ_INDEX_NOT_FOUND || !haplotypeInsertLocationAndOperator.getRight().consumesReadBases() ) {
            return null;
        }
        final int haplotypeInsertLocation = haplotypeInsertLocationAndOperator.getLeft();

        // can't insert if we don't have any sequence after the inserted alt allele to span the new variant
        if (haplotypeInsertLocation + refAllele.length() > myBases.length) {
            return null;
        }

        byte[] newHaplotypeBases = {};
        newHaplotypeBases = ArrayUtils.addAll(newHaplotypeBases, ArrayUtils.subarray(myBases, 0, haplotypeInsertLocation)); // bases before the variant
        newHaplotypeBases = ArrayUtils.addAll(newHaplotypeBases, altAllele.getBases()); // the alt allele of the variant
        newHaplotypeBases = ArrayUtils.addAll(newHaplotypeBases, ArrayUtils.subarray(myBases, haplotypeInsertLocation + refAllele.length(), myBases.length)); // bases after the variant
        return new Haplotype(newHaplotypeBases);
    }

    /**
     * Get the weakest edge weight (the minimum reads count to support this haplotype) of this haplotype
     * @return an integer, where higher values are better
     */
    public int getWeakness() {
        return weakness;
    }

    /**
     * Set the weakness (the minimum reads count to support this haplotype) of this haplotype.
     *
     * Note that if this is the reference haplotype it is always given INT.MAX_VALUE score
     *
     * @param weakness an integer, where higher values are better
     */
    public void setWeakness(final int weakness) {
        this.weakness = weakness;
    }

    /**
     * Get the score (an estimate of the support) of this haplotype
     * @return a double, where higher values are better
     */
    public double getScore() {
        return score;
    }

    /**
     * Set the score (an estimate of the support) of this haplotype.
     *
     * Note that if this is the reference haplotype it is always given Double.MAX_VALUE score
     *
     * @param score a double, where higher values are better
     */
    public void setScore(final double score) {
        this.score = score;
    }

    /**
     * Get the span of this haplotype (may be null)
     * @return a potentially null genome loc
     */
    public Locatable getGenomeLocation() {
        return genomeLocation;
    }

    public boolean isCollapsed() {
        return isCollapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.isCollapsed = collapsed;
    }

    public int getUniquenessValue() {
        return uniquenessValue;
    }
    public int getKmerSize() {
        return kmerSize;
    }

    public void setUniquenessValue(int uniquenessValue) {
        this.uniquenessValue = uniquenessValue;
    }
    public void setKmerSize(int kmerSize) {
        this.kmerSize = kmerSize;
    }

    // Flag used to control how the haplotype is treated by the hmm and downstream code for the PDHMM.
    public boolean isPartiallyDetermined() { return false; }
}
