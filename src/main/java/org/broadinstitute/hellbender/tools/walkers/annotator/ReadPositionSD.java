package org.broadinstitute.hellbender.tools.walkers.annotator;

import com.google.common.primitives.Ints;
import htsjdk.variant.variantcontext.VariantContext;
import org.broadinstitute.barclay.help.DocumentedFeature;
import org.broadinstitute.hellbender.utils.MathUtils;
import org.broadinstitute.hellbender.utils.help.HelpConstants;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.variant.GATKVCFConstants;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * The standard deviation of distance of variant starts from ends of reads supporting each alt allele.
 *
 * </p>The output is an array containing, for each alt allele, the standard deviation of distance of the variant start from the closest read end over all reads that best match that allele.</p>
 * </p> See details in distance calculation in the ReadPostion description </p>
 * <p>This annotation is useful for filtering alignment artifacts.</p>
 */
@DocumentedFeature(groupName=HelpConstants.DOC_CAT_ANNOTATORS, groupSummary=HelpConstants.DOC_CAT_ANNOTATORS_SUMMARY, summary="andard deviation of distance of variant starts from ends of reads supporting each allele (POSSD)")
public class ReadPositionSD extends PerAlleleAnnotation implements StandardMutectAnnotation {

    private static final int VALUE_FOR_NO_READS = 0;

    @Override
    protected int aggregate(final List<Integer> values) {
        return values.isEmpty() ? VALUE_FOR_NO_READS : MathUtils.mad(Ints.toArray(values));
    }

    @Override
    protected String getVcfKey() { return GATKVCFConstants.READ_POSITON_SD_KEY; }

    @Override
    protected OptionalInt getValueForRead(final GATKRead read, final VariantContext vc) {
        return getPosition(read, vc);
    }

    public static OptionalInt getPosition(final GATKRead read, final VariantContext vc) {
        if (vc.getStart() < read.getStart() || read.getEnd() < vc.getStart()) {
            return OptionalInt.empty();
        }
        final OptionalDouble valueAsDouble = ReadPosRankSumTest.getReadPosition(read, vc);
        return valueAsDouble.isPresent() ? OptionalInt.of((int) valueAsDouble.getAsDouble()) : OptionalInt.empty();
    }
}
