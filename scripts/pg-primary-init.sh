#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE replication WITH REPLICATION LOGIN PASSWORD 'notify';
EOSQL

# Allow replication connections from any host
echo "host replication replication 0.0.0.0/0 md5" >> /var/lib/postgresql/data/pg_hba.conf
