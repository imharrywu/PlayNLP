#!/bin/bash

java -cp parser/*: edu.stanford.nlp.parser.lexparser.LexicalizedParser \
    -encoding utf-8 \
    -outputFormat "penn,typedDependenciesCollapsed" \
    ./trained.ser.gz \
    $1
