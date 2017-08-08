#!/bin/bash

java    -mx500m -cp parser/*: edu.stanford.nlp.parser.lexparser.LexicalizedParser \
        -encoding utf-8 \
        -sentences newline \
        -tokenized \
        -tagSeparator / \
        -tokenizerFactory edu.stanford.nlp.process.WhitespaceTokenizer \
        -tokenizerMethod newCoreLabelTokenizerFactory \
        -outputFormat "penn,typedDependenciesCollapsed" \
        edu/stanford/nlp/models/lexparser/chinesePCFG.ser.gz \
        $1