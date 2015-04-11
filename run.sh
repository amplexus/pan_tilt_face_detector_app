#!/bin/bash

java -Djava.library.path=./lib -cp resources/:lib/opencv-246.jar:bin org.amplexus.opencv.app.DetectFaceDemo
