#!/bin/bash

java -mx2g -cp segmenter/*: edu.stanford.nlp.ie.crf.CRFClassifier \
     -sighanCorporaDict ./segmenter/data \
     -textFile $1 \
     -inputEncoding UTF-8 \
     -sighanPostProcessing true \
     -keepAllWhitespaces false \
     -loadClassifier ./segmenter/data/ctb.gz \
     -serDictionary ./segmenter/data/dict-chris6.ser.gz,./dict.txt