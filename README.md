ABOUT FACE RECOGNITION

OpenCV object recognition is based on the Viola-Jones object detection framework - http://en.wikipedia.org/wiki/Viola%E2%80%93Jones_object_detection_framework.

The Viola-Jones framework involves summing image pixels within a rectangular area. These rectangular areas are classified as features, and object recognition is based on combinations of these rectangular areas (features). OpenCV allows you to describe classifiers in XML files which define particular types of objects you're looking to detect.

To improve performance, rather than evaluating every possible rectangular area at every possible image resolution, the Viola-Jones framework uses a learning algorithm called AdaBoost (http://en.wikipedia.org/wiki/AdaBoost) to both select the best features and to train classifiers that use them.

To further boost performance, the framework uses a cascading classifier algorithm, whereby the fastest / simplest classifiers are evaluated first, and if they pass, then more complex classifiers are evaluated next and so on, til the most expensive and complex classifiers are evaluated last. This allows the algorithm to quickly eliminate the vast majority of false positives.
