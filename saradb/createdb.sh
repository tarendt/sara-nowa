#!/bin/bash -ex
BASEDIR=$(readlink -f $(dirname "$0"))
cd $BASEDIR
DB=$1
USER=$2
ENV=$3

createuser -l -D -R -S $USER
createdb -E UTF8 -O $USER $DB

psql -d $DB -f ./adminconfig.sql
psql -d $DB -f ./schema.sql
sed "s/__USERNAME__/$USER/g" permissions.sql | psql -d $DB
# FIXME should just run the license updater instead!
psql -d $DB -f ./licenses.sql

for file in "$BASEDIR/$ENV"/*.sql; do
	psql -d $DB -v "basedir=$BASEDIR/$ENV" -f "$file"
done