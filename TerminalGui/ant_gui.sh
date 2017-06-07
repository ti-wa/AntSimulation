#!/bin/bash
while true; do 
	curl -X GET http://$1/ants
	sleep 1
	echo "----------------------------------------------------------"
done
