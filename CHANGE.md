#GATK4 允英维护版本主要更新内容:

1. Active Region 增加 active 条件，针对深度较高的低频突变有较高的灵敏度，同时保证了分析时长的可控性

2. 修改 force allele 模式下，指定的位点与 Haplotype 中突变位置重合但突变内容不符情况下的添加规则，从而保证后续 Haplotype 的准确性

3. 增加 VCF 字段: 突变在read中位置的标准差 POSSD，用来过滤某些假阳性位点(可能由于酶切等原因导致)

