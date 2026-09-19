# Audited build input and runtime profiles

Runtime reads the compiled binary, not research documents. The JSON build snapshot is a deterministic subset of the uploaded Pack: derived facts, source/license metadata and CC BY 4.0 leader coordinates. No AntWeb pictures, thesis/article/video bytes or reference-only code are redistributed.

Profile version: `2026.09.17-donor-transfer.2`; schema: `1`.
Binary SHA-256: `ce558669b3b17d5e85282c1bc94ea8c0e34b6d86311ded3a2acc2d31552c502b`.

## Source mappings

| Runtime parameter | Unit | Source field and context |
| --- | --- | --- |
| abdomen_length_fraction | Scalar | engineering:abdomen_length_fraction / profile-compiler/src/lib.rs / abdomen_length_fraction (EngineeringAssumption) |
| acceleration_mm_s2 | MillimetersPerSecondSquared | dataset-valentini-2020-tandem / 08_DERIVED_BIOLOGY_DATABASE/acceleration.csv / p05;p95 (DonorTransfer); paper-valentini-2020-information / 08_DERIVED_BIOLOGY_DATABASE/acceleration.csv / p05;p95 (DonorTransfer) |
| angular_velocity_rad_s | RadiansPerSecond | dataset-valentini-2020-tandem / 08_DERIVED_BIOLOGY_DATABASE/turn_parameters.csv / p95;p95 (DonorTransfer); paper-valentini-2020-information / 08_DERIVED_BIOLOGY_DATABASE/turn_parameters.csv / p95;p95 (DonorTransfer) |
| antenna_filter_s | Seconds | engineering:antenna_filter_s / profile-compiler/src/lib.rs / antenna_filter_s (EngineeringAssumption) |
| antenna_sweep_rad | Radians | engineering:antenna_sweep_rad / profile-compiler/src/lib.rs / antenna_sweep_rad (EngineeringAssumption); paper-draft-2018-antennae / 08_DERIVED_BIOLOGY_DATABASE/antenna_motion.csv / state;head_coupling_observation;source_ids (DonorTransfer) |
| antenna_target_interval_s | Seconds | engineering:antenna_target_interval_s / profile-compiler/src/lib.rs / antenna_target_interval_s (EngineeringAssumption) |
| body_length_mm | Millimeters | dataset-antweb-specimen-images / 08_DERIVED_BIOLOGY_DATABASE/body_dimensions.csv / min_mm;max_mm (Measurement) |
| body_width_ratio_to_head | Scalar | engineering:body_width_ratio_to_head / profile-compiler/src/lib.rs / body_width_ratio_to_head (EngineeringAssumption) |
| climb_down_speed_mm_s | MillimetersPerSecond | thesis-cao-2023-gravity-climbing / 08_DERIVED_BIOLOGY_DATABASE/climbing_parameters.csv / speed_mean_mm_s;speed_mean_mm_s (Measurement) |
| climb_up_speed_mm_s | MillimetersPerSecond | thesis-cao-2023-gravity-climbing / 08_DERIVED_BIOLOGY_DATABASE/climbing_parameters.csv / speed_mean_mm_s;speed_mean_mm_s (Measurement); paper-federle-2001-arolium / 08_DERIVED_BIOLOGY_DATABASE/climbing_parameters.csv / speed_mean_mm_s;speed_mean_mm_s (Measurement) |
| cursor_radius_mm | Millimeters | engineering:cursor_radius_mm / profile-compiler/src/lib.rs / cursor_radius_mm (EngineeringAssumption) |
| direction_persistence_s | Seconds | dataset-valentini-2020-tandem / 02_RAW_ANT_DATASETS/tracked_trajectories/valentini-2020/primary_normalized_tracks.csv / contiguous abs(turn_rate)<donor median and speed>donor p10; p05/p95 duration (DerivedMeasurement) |
| donor_low_motion_threshold_mm_s | MillimetersPerSecond | dataset-valentini-2020-tandem / 08_DERIVED_BIOLOGY_DATABASE/stop_durations.csv / threshold_mm_s;threshold_mm_s (DerivedMeasurement); paper-valentini-2020-information / 08_DERIVED_BIOLOGY_DATABASE/stop_durations.csv / threshold_mm_s;threshold_mm_s (DerivedMeasurement) |
| donor_median_speed_mm_s | MillimetersPerSecond | dataset-valentini-2020-tandem / 08_DERIVED_BIOLOGY_DATABASE/walking_speed.csv / median;median (DerivedMeasurement); paper-valentini-2020-information / 08_DERIVED_BIOLOGY_DATABASE/walking_speed.csv / median;median (DerivedMeasurement) |
| donor_p95_speed_mm_s | MillimetersPerSecond | dataset-valentini-2020-tandem / 08_DERIVED_BIOLOGY_DATABASE/walking_speed.csv / p95;p95 (DerivedMeasurement); paper-valentini-2020-information / 08_DERIVED_BIOLOGY_DATABASE/walking_speed.csv / p95;p95 (DerivedMeasurement) |
| edge_exit_probability | Scalar | engineering:edge_exit_probability / profile-compiler/src/lib.rs / edge_exit_probability (EngineeringAssumption) |
| edge_zone_body_lengths | Scalar | engineering:edge_zone_body_lengths / profile-compiler/src/lib.rs / edge_zone_body_lengths (EngineeringAssumption) |
| encounter_radius_body_lengths | Scalar | engineering:encounter_radius_body_lengths / profile-compiler/src/lib.rs / encounter_radius_body_lengths (EngineeringAssumption) |
| encounter_response_probability | Scalar | engineering:encounter_response_probability / profile-compiler/src/lib.rs / encounter_response_probability (EngineeringAssumption) |
| gait_stance_fraction | Scalar | engineering:gait_stance_fraction / profile-compiler/src/lib.rs / gait_stance_fraction (EngineeringAssumption); paper-clifton-2020-uneven / 08_DERIVED_BIOLOGY_DATABASE/gait_parameters.csv / phase_group_a;phase_group_b (DonorTransfer); paper-reinhardt-2009-locomotion / 08_DERIVED_BIOLOGY_DATABASE/gait_parameters.csv / phase_group_a;phase_group_b (DonorTransfer) |
| head_length_mm | Millimeters | dataset-antweb-specimen-images / 08_DERIVED_BIOLOGY_DATABASE/body_dimensions.csv / min_mm;max_mm (Measurement) |
| head_width_mm | Millimeters | dataset-antweb-specimen-images / 08_DERIVED_BIOLOGY_DATABASE/body_dimensions.csv / min_mm;max_mm (Measurement) |
| leg_reach_body_fraction | Scalar | engineering:leg_reach_body_fraction / profile-compiler/src/lib.rs / leg_reach_body_fraction (EngineeringAssumption) |
| leg_thickness_mm | Millimeters | engineering:leg_thickness_mm / profile-compiler/src/lib.rs / leg_thickness_mm (EngineeringAssumption) |
| pause_duration_s | Seconds | dataset-valentini-2020-tandem / 08_DERIVED_BIOLOGY_DATABASE/stop_durations.csv / p05;p95 (DonorTransfer); paper-valentini-2020-information / 08_DERIVED_BIOLOGY_DATABASE/stop_durations.csv / p05;p95 (DonorTransfer) |
| preferred_speed_mm_s | MillimetersPerSecond | paper-clifton-2020-uneven / 08_DERIVED_BIOLOGY_DATABASE/stride_parameters.csv / speed_bin_mm_s;speed_bin_mm_s (Measurement) |
| speed_variation_fraction | Scalar | engineering:speed_variation_fraction / profile-compiler/src/lib.rs / speed_variation_fraction (EngineeringAssumption) |
| stride_length_mm | Millimeters | paper-clifton-2020-uneven / 08_DERIVED_BIOLOGY_DATABASE/stride_parameters.csv / stride_length_min_mm;stride_length_max_mm (Measurement) |
| thorax_length_fraction | Scalar | engineering:thorax_length_fraction / profile-compiler/src/lib.rs / thorax_length_fraction (EngineeringAssumption) |
| trail_cell_mm | Millimeters | engineering:trail_cell_mm / profile-compiler/src/lib.rs / trail_cell_mm (EngineeringAssumption) |
| trail_decay_s | Seconds | engineering:trail_decay_s / profile-compiler/src/lib.rs / trail_decay_s (EngineeringAssumption) |
| trail_turn_weight | Scalar | engineering:trail_turn_weight / profile-compiler/src/lib.rs / trail_turn_weight (EngineeringAssumption); paper-perna-2012-trail-pattern / 08_DERIVED_BIOLOGY_DATABASE/behavior_states.csv / state;source_ids (DonorTransfer); paper-choe-2012-trail-pheromone / 08_DERIVED_BIOLOGY_DATABASE/behavior_states.csv / state;source_ids (DonorTransfer) |
| velocity_filter_s | Seconds | engineering:velocity_filter_s / profile-compiler/src/lib.rs / velocity_filter_s (EngineeringAssumption) |

## Attribution

Valentini and colleagues (2020), *Data and code from: Revealing the structure of information flows discriminates similar animal social behaviors*, Figshare DOI 10.6084/m9.figshare.9786260.v1, CC BY 4.0. Original calibrated coordinates were selected to leader tracks, first 900 seconds and every tenth original frame; speeds, angular velocities and operational statistics were derived. No endorsement is implied.

Clifton, Holway and Gravish (2020), *Uneven substrates constrain walking speed in ants through modulation of stride frequency more than stride length*, DOI 10.1098/rsos.192068, CC BY 4.0. Numerical summary facts are used with their experimental context.

AntWeb specimen/taxon records provide numerical morphology facts only. Individual-image licenses were not cleared for app distribution; no images are shipped. Other listed works support factual/qualitative reimplementation only. See the complete provenance report for limitations and review conflicts.

The runtime transfer is an engineering model, not a validated reconstruction of Argentine-ant exploration. Central-quantile outlier handling is explicit and raw donor samples are retained, never silently altered.
