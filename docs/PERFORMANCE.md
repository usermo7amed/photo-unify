# Performance profiling

This guide records measurements from a device instead of treating an unmeasured code change as a performance win.

## Signals added in the app

The debug log tag is `PhotoUnifierPerf`. Each processed image reports:
- source dimensions and elapsed time for decode, classification, source preview, enhancement, enhanced preview, brightness/contrast adjustment, save, and saved preview;
- total time for the image;
- JVM heap usage and Android native-heap allocation at the log point.

The repository also adds Android trace sections named `PU.decode`, `PU.classify`, `PU.preview.source`, `PU.enhance`, `PU.preview.enhanced`, `PU.adjust`, `PU.save`, and `PU.preview.saved`. These can be inspected in a System Trace / Perfetto capture. The log deliberately omits image names, paths, and URI values.

## Repeatable test procedure

1. Install the same debug APK on a physical device; note device model, Android version, available RAM, and whether the run is warm or cold.
2. In Android Studio Profiler or with Perfetto, start a memory/system trace before launching Photo Unifier.
3. Clear the log buffer and capture only the performance tag:

   ```bash
   adb logcat -c
   adb logcat -s PhotoUnifierPerf:I '*:S'
   ```

4. Run each batch size at least three times with a comparable mix of document scans and color photographs. Recommended batches: 1, 10, and 25 images. For each run, record cold-start-to-first-preview time, total batch time, median per-image time, maximum Java/native heap observed in the profiler, failed images, and any UI freezes or process restarts.
5. Repeat with the same input set after each optimization. Compare medians, not a single fastest run. Keep the same device, power mode, image set, and application state.

## Memory policy being checked

- The first screen decodes its main preview to a maximum long side of 1280 pixels.
- The home screen loads at most six selection thumbnails, each with a 256-pixel maximum long side.
- Crop-handle movement renders a bounded preview; applying the crop still produces a full-resolution result.
- Batch processing remains sequential. Full-resolution source, enhanced, and adjusted bitmaps are local to one image and recycled in the processing loop; UI callbacks receive bounded preview bitmaps.

## Reporting

Record actual device measurements in the pull request or release notes. No benchmark numbers are hard-coded here because this repository change cannot simulate a representative physical-device run; performance gains should only be claimed after these steps are run on target hardware.
