#!/bin/bash

java  -cp parser/stanford-parser.jar  -mx800m edu.stanford.nlp.parser.lexparser.LexicalizedParser \
    -evals "factDA,tsv" \
    -chineseFactored -PCFG -hMarkov 1 -nomarkNPconj -compactGrammar 0 \
    -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams \
    -PCFG \
    -chinesePCFG \
    -saveToSerializedFile ./trained-0.ser.gz \
    -maxLength 40 \
    -encoding utf-8 \
    -train $1 \
    -test $1