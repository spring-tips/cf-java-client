#!/bin/bash

rm hi.jar 
spring jar hi.jar hi.groovy
#cf push -p hi.jar  $1 
