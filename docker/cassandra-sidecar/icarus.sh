#!/bin/bash

exec -a "icarus" java \
    -XX:+UnlockExperimentalVMOptions -XX:MaxRAMFraction=1 -XshowSettings:vm  \
    -jar /opt/lib/icarus/icarus.jar icarus \
    "$@"