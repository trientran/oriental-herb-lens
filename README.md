[![Build Status](https://travis-ci.org/firebase/mlkit-material-android.svg?branch=master)](https://travis-ci.org/firebase/mlkit-material-android)

# Oriental Herb Lens

## How to use the app

This app supports three herb image recognition scenarios: live camera, single static image, and
multiple images.

### Live Camera scenario

It uses the camera preview as input and contains three workflow: object detection & visual search,
object detection & custom classification, and barcode detection. There's also a Settings page to
allow you to configure several options:
- Camera
  - Preview Size - Specify the preview size of rear camera manually (Default size is chose appropriately based on screen size)
- Object detection
    - Enable Multiple Objects -- Enable multiple objects to be detected at once.
    - Enable classification -- Enable coarse classification
- Product search
    - Enable auto search -- If enabled, search request will be fired automatically once object is detected and confirmed, otherwise a search button will appear to trigger search manually
    - Confirmation time in manual search -- Required time that an manually-detected object needs to be in focus before it is confirmed.
    - Confirmation time in auto search -- Required time that an auto-detected object needs to be in focus before it is confirmed.
- Barcode detection
    - Barcode reticle width -- Size of barcode reticle width relative to the camera preview width
    - Barcode reticle height -- Size of the barcode reticle height relative to the camera preview height
    - Enable Barcode size check -- Will prompt user to "move closer" if the detected barcode is too small
    - Delay loading result -- Simulates a case where the detected barcode requires further
      processing before displaying the result.

### Single Static Image scenario

During this scenario, the app will prompt the user to select an image from the “Image Picker” (
gallery), detect objects in the selected image, and then perform visual search on those objects.
There are well designed UI components (overlay dots, card carousel etc.) to indicate the detected
objects and search results.

### Multiple Static Images scenario

### Full-text search

## Copyright

© Uri Lee, 2020.
