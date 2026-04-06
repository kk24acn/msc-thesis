use redb::{ Database, ReadableDatabase, TableDefinition };
use std::sync::Arc;

const KEYSHARES_TABLE: TableDefinition<&str, &[u8]> = TableDefinition::new("keyshares");

#[derive(Clone)]
pub struct KeyshareStore {
    db: Arc<Database>,
}

impl KeyshareStore {
    pub fn new(db_path: &str) -> Result<Self, String> {
        let db = Database::create(db_path).map_err(|e| e.to_string())?;
        let write_txn = db.begin_write().map_err(|e| e.to_string())?;
        write_txn.open_table(KEYSHARES_TABLE).map_err(|e| e.to_string())?;
        write_txn.commit().map_err(|e| e.to_string())?;
        Ok(Self { db: Arc::new(db) })
    }

    pub fn exists(&self, key: &str) -> Result<bool, Box<dyn std::error::Error>> {
        let read_txn = self.db.begin_read()?;
        let table = read_txn.open_table(KEYSHARES_TABLE)?;
        let exists = table.get(key)?.is_some();
        Ok(exists)
    }

    pub fn save_keyshare(&self, session_id: &str, keyshare_data: &[u8]) -> Result<(), String> {
        let write_txn = self.db.begin_write().map_err(|e| e.to_string())?;
        {
            let mut table = write_txn.open_table(KEYSHARES_TABLE).map_err(|e| e.to_string())?;
            table.insert(session_id, keyshare_data).map_err(|e| e.to_string())?;
        }
        write_txn.commit().map_err(|e| e.to_string())?;
        Ok(())
    }

    pub fn get_keyshare(&self, key: &str) -> Result<Option<Vec<u8>>, Box<dyn std::error::Error>> {
        let read_txn = self.db.begin_read()?;
        let table = read_txn.open_table(KEYSHARES_TABLE)?;
        let value = table.get(key)?.map(|v| v.value().to_vec());
        Ok(value)
    }
}
