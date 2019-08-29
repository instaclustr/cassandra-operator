#!/bin/bash

RESTORE_MARKING_FILE="/var/lib/cassandra/restore_done"

declare -a args

for arg in $@; do
    case ${arg} in
        --storage-location=*)
            args+=("$arg/$(hostname)")
        shift
        ;;
        *)
            args+=(${arg})
        shift
        ;;
    esac
done

if [[ "${args[0]}" == "restore" ]]; then
    if [[ ! -f ${RESTORE_MARKING_FILE} ]]; then
        echo "Executing restore command with arguments:" ${args[*]}
        java -jar /opt/lib/cassandra-backup-restore/cassandra-backup-restore.jar ${args[*]}
        if [[ ! "$?" == "0" ]]; then exit 1; fi
        echo "${args[*]}" > ${RESTORE_MARKING_FILE}
    else
        echo "Restoration was already done, not restoring again."
        cat ${RESTORE_MARKING_FILE}
    fi
elif [[ "${args[0]}" == "backup" ]]; then
        echo "Executing backup command with arguments:" ${args[*]}
        java -jar /opt/lib/cassandra-backup-restore/cassandra-backup-restore.jar ${args[*]}
        if [[ ! "$?" == "0" ]]; then exit 1; fi
else
    echo "The first argument of script ${0} has to be either 'backup' or 'restore'. It was ${args[0]}"
    exit 1
fi