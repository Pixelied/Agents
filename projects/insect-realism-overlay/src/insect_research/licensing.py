from dataclasses import dataclass

from insect_research.schema import LicenseClass


@dataclass(frozen=True, slots=True)
class InclusionDecision:
    bundle_bytes: bool
    ship_with_app: bool
    may_derive_facts: bool


_POLICY = {
    LicenseClass.REDISTRIBUTABLE_AND_SHIPPABLE: InclusionDecision(True, True, True),
    LicenseClass.REDISTRIBUTABLE_RESEARCH_ONLY: InclusionDecision(True, False, True),
    LicenseClass.REFERENCE_ONLY: InclusionDecision(False, False, True),
    LicenseClass.DO_NOT_USE: InclusionDecision(False, False, False),
}


def decide_inclusion(license_class: LicenseClass) -> InclusionDecision:
    return _POLICY[license_class]
