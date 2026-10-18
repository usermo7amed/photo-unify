import unittest

import cv2
import numpy as np

from tools.prototype.enhance import (
    _apply_clahe,
    enhance,
    local_tone_map,
    rectify_page,
    remove_paper_shadows,
)


class EnhancementTests(unittest.TestCase):
    def test_local_tone_map_preserves_highlight_headroom_and_gradation(self):
        gradient = np.tile(np.linspace(20, 250, 512, dtype=np.uint8), (80, 1))
        config = {
            "target": 0.50, "alpha": 0.50,
            "gain_min": 0.62, "gain_max": 2.25,
        }
        corrected = local_tone_map(gradient, config)
        self.assertEqual(corrected.shape, gradient.shape)
        self.assertLessEqual(int(corrected.max()), 250)
        self.assertGreater(np.unique(corrected).size, 150)

    def test_clahe_blending_retains_input_tonal_detail(self):
        gradient = np.tile(np.linspace(18, 242, 512, dtype=np.uint8), (80, 1))
        corrected = _apply_clahe(gradient, 2.0)
        self.assertEqual(corrected.shape, gradient.shape)
        self.assertEqual(corrected.dtype, np.uint8)
        self.assertGreater(np.unique(corrected).size, 150)

    def test_paper_shadow_correction_reduces_background_variation(self):
        height, width = 320, 400
        background = np.tile(np.linspace(135, 225, width, dtype=np.float32), (height, 1))
        # Simulate dark text without making it part of the estimated large-scale field.
        luminance = np.clip(background, 0, 255).astype(np.uint8)
        cv2.putText(luminance, "Text 123", (45, 165), cv2.FONT_HERSHEY_SIMPLEX, 1.2, 35, 3)
        corrected = remove_paper_shadows(luminance)
        original_paper = luminance[20:45, :]
        corrected_paper = corrected[20:45, :]
        self.assertLess(float(corrected_paper.std()), float(original_paper.std()))
        self.assertGreater(int(corrected[165, 85]), 0)

    def test_perspective_rectification_finds_skewed_page(self):
        canvas = np.full((300, 320, 3), 80, dtype=np.uint8)
        corners = np.array([[75, 35], [245, 52], [230, 265], [58, 245]], dtype=np.int32)
        cv2.fillConvexPoly(canvas, corners, (235, 235, 235))
        rectified = rectify_page(canvas)
        self.assertNotEqual(rectified.shape[:2], canvas.shape[:2])
        self.assertGreater(rectified.shape[0], 150)
        self.assertGreater(rectified.shape[1], 150)

    def test_mode_routes_return_valid_images(self):
        image = np.full((192, 240, 3), 120, dtype=np.uint8)
        cv2.rectangle(image, (25, 20), (215, 172), (190, 200, 220), -1)
        cv2.putText(image, "TEST", (60, 105), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (35, 45, 55), 2)
        for mode in ("auto", "interior", "exterior", "document", "poster"):
            with self.subTest(mode=mode):
                result = enhance(image, mode)
                self.assertEqual(result.dtype, np.uint8)
                self.assertEqual(result.ndim, 3)
                self.assertEqual(result.shape[2], 3)
                self.assertGreater(result.size, 0)


if __name__ == "__main__":
    unittest.main()