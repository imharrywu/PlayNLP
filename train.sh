#!/bin/bash

java  -cp parser/*:  -mx800m edu.stanford.nlp.parser.lexparser.LexicalizedParser \
    -evals "factDA,tsv" \
    -chineseFactored -PCFG -hMarkov 1 -nomarkNPconj -compactGrammar 0 \
    -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams \
    -PCFG \
    -chinesePCFG \
    -saveToSerializedFile ./trained.ser.gz \
    -maxLength 40 \
    -encoding utf-8 \
    -train $1 \
    -test $1