# R8.3 empirical-data and reference-media policy

## Goal

Maximize **implementation-relevant empirical evidence**, not archive size.

A 20 KB spreadsheet containing per-individual behavior observations outranks a multi-gigabyte genome read archive for this project.

## Empirical data classes

### raw_repository_data
Original study data deposited in Dryad, Zenodo, Edmond, Mendeley Data or an equivalent research repository.

### raw_publisher_supplement
Original or source-level supplementary XLS/XLSX/CSV/data archive published with the paper.

### study_video
Original study footage or supplementary behavior video whose reuse permits bundling.

### published_aggregate_extract
Machine-readable transcription of numerical values explicitly published in a paper/table. These are real measurements but **not raw observations**.

### processed_or_contextual_data
Exact-species data useful for morphology, physiology, stage context or validation but not a direct runtime movement calibration.

### remote_only_restricted
Important source data or study video that is known to exist but cannot be redistributed here or could not be acquired reliably. These entries carry exact acquisition/source instructions.

## Authority

For runtime biology:

1. raw exact species + exact target stage;
2. raw exact species, other stage/form;
3. published exact-species aggregates;
4. same-genus/family donor evidence;
5. engineering assumptions;
6. visual references.

Never silently promote a lower class.

## Reference-media curation

R8.2 contained 1,378 exact-species media files. R8.3 targets approximately **55% retained by file count**, deleting about 45% of the bundled images.

Selection is deterministic and favors:
- target-stage labels;
- stage/form metadata;
- useful source/creator metadata;
- image resolution;
- diversity across source dataset, stage label and aspect ratio.

Removed files stay in `DROPPED_REFERENCE_MEDIA.csv` with original URL and SHA-256. They are removed from the ZIP, not erased from provenance.

## No byte-padding

Do not bundle huge genome/SRA/FASTQ archives solely to hit a size target.

Genomic or transcriptomic material is bundled only when a compact study table materially helps stage, physiology or variation modeling. Large sequencing archives should remain remotely indexed.

## Spreadsheet validation

All acquired XLS/XLSX/CSV files must:
- open/parse successfully;
- retain original bytes;
- receive SHA-256;
- have a manifest row;
- be classified by study and stage;
- never be converted into runtime constants without reading the relevant columns/methods.

The release validator inventories workbook sheet names/dimensions to catch corrupt or fake downloads without rewriting source workbooks.
