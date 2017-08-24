#!/bin/bash

java -cp parser/*: edu.stanford.nlp.parser.lexparser.LexicalizedParser \
    -encoding utf-8 \
    -outputFormat "penn,typedDependenciesCollapsed" \
    -v \
    -model ./trained-0.ser.gz \
    -test $1
