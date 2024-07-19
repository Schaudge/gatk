package org.broadinstitute.hellbender.tools.walkers.haplotypecaller.graphs;

import org.broadinstitute.hellbender.utils.MathUtils;
import org.broadinstitute.hellbender.utils.haplotype.Haplotype;

import java.util.List;

/**
 * Represents a result from a K-best haplotype search.
 *
 * @author Valentin Ruano-Rubio &lt;valentin@broadinstitute.org&gt;
 */
public class KBestHaplotype<V extends BaseVertex, E extends BaseEdge> extends Path<V, E>{
    private final int weakness;
    private final double score;
    private boolean isReference;

    public int weakness() { return weakness; }
    public double score() { return score; }
    public boolean isReference() { return isReference; }

    public KBestHaplotype(final V initialVertex, final BaseGraph<V,E> graph) {
        super(initialVertex, graph);
        weakness = Integer.MAX_VALUE;
        score = 0;
    }

    public KBestHaplotype(final KBestHaplotype<V, E> p, final E edge, final int totalOutgoingMultiplicity) {
        super(p, edge);
        weakness = totalOutgoingMultiplicity == 1 ? p.weakness : Math.min(p.weakness, edge.getMultiplicity());
        score = p.score + computeLogPenaltyScore( edge.getMultiplicity(), totalOutgoingMultiplicity);
        isReference &= edge.isRef();
    }

    public static double computeLogPenaltyScore(int edgeMultiplicity, int totalOutgoingMultiplicity) {
        return Math.log10(edgeMultiplicity) - Math.log10(totalOutgoingMultiplicity);
    }

    public KBestHaplotype(final KBestHaplotype<V, E> p, final List<E> edgesToExtend, final double edgePenalty) {
        super(p, edgesToExtend);
        weakness = p.weakness();
        score = p.score() + edgePenalty;
        isReference &= edgesToExtend.get(edgesToExtend.size() - 1).isRef();
    }

    public final Haplotype haplotype() {
        final Haplotype haplotype = new Haplotype(getBases(),isReference());
        haplotype.setWeakness(weakness());
        haplotype.setScore(score());
        return haplotype;
    }
}
