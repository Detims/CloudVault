CREATE KEYSPACE IF NOT EXISTS cloudvault
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS cloudvault.documents (
    owner_id text,
    document_id uuid,
    file_name text,
    content_type text,
    size_bytes bigint,
    object_key text,
    status text,
    created_at timestamp,
    PRIMARY KEY ((owner_id), document_id)
) WITH CLUSTERING ORDER BY (document_id DESC);
