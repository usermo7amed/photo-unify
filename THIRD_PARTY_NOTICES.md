# Third-party notices

## FairScan document segmentation model

- Component: `fairscan-segmentation-model.tflite`
- Upstream project: https://github.com/pynicolas/fairscan-segmentation-model
- Release: `v1.2.0`
- License: GNU General Public License, version 3.0 (GPL-3.0-only as distributed by the upstream release)
- Model asset SHA-256: `96e14d7e610dd0c27b768b228fbc553b4ec119ebe68f3a3594029a25400691d2`
- License asset: `LICENSE.txt` from the same release; its SHA-256 is checked during the build.
- Copyright: FairScan model authors; see the included license text for the complete notice and terms.

The Android build downloads the published model from the pinned upstream release and verifies its SHA-256 before packaging it. The complete upstream license text is packaged as `FAIRSCAN-LICENSE.txt` in the app assets.

**Distribution note:** the model is GPL-3.0. Before distributing an APK that contains it, review the GPL obligations for the combined application, including providing the corresponding source and license notices. This notice is not legal advice. The model is optional at runtime: if it cannot be loaded or produces no acceptable quadrilateral, Photo Unifier falls back to its existing OpenCV detector.
