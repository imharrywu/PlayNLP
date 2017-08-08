#!/bin/bash

java    -cp parser/*: edu.stanford.nlp.parser.lexparser.LexicalizedParser \
	-encoding utf-8 \
	-outputFormat "penn,typedDependenciesCollapsed" \
	edu/stanford/nlp/models/lexparser/xinhuaFactoredSegmenting.ser.gz \
	$1