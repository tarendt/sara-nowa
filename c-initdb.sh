#!/bin/bash

# initialize postgresql from within eclipse terminal
# this will run inside container...
./udocker.py --repo=/udocker run  -v $PWD/temp.sql:/init.sql  -v $PWD/postgresql.sh:/init.sh -i -t --rm --user postgres  c1t4r/sara-server-vre   /init.sh