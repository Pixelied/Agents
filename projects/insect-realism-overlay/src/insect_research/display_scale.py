MM_PER_INCH = 25.4


def mm_to_pixels(mm: float, ppi: float) -> float:
    if mm <= 0 or ppi <= 0:
        raise ValueError("mm and ppi must be positive")
    return mm / MM_PER_INCH * ppi


def pixels_to_mm(px: float, ppi: float) -> float:
    if px < 0 or ppi <= 0:
        raise ValueError("px must be non-negative and ppi positive")
    return px / ppi * MM_PER_INCH
