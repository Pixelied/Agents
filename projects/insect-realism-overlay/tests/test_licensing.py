import pytest

from insect_research.licensing import decide_inclusion
from insect_research.schema import LicenseClass


@pytest.mark.parametrize(
    ("license_class", "bundle_bytes", "ship_with_app"),
    [
        (LicenseClass.REDISTRIBUTABLE_AND_SHIPPABLE, True, True),
        (LicenseClass.REDISTRIBUTABLE_RESEARCH_ONLY, True, False),
        (LicenseClass.REFERENCE_ONLY, False, False),
        (LicenseClass.DO_NOT_USE, False, False),
    ],
)
def test_inclusion_policy(license_class, bundle_bytes, ship_with_app) -> None:
    decision = decide_inclusion(license_class)
    assert decision.bundle_bytes is bundle_bytes
    assert decision.ship_with_app is ship_with_app
