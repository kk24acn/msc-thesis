use redb::{ Database, ReadableDatabase, ReadableTable, TableDefinition };

const KEYSHARES_TABLE: TableDefinition<&str, &[u8]> = TableDefinition::new("keyshares");

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let db = Database::create("node0.redb")?;
    let read_txn = db.begin_read()?;
    let table = read_txn.open_table(KEYSHARES_TABLE)?;

    println!("--- Reading redb keyshares ---");
    for item in table.iter()? {
        let (key, value) = item?;
        println!("Key: {:<20} | Data Size: {} bytes", key.value(), value.value().len());
    }

    Ok(())
}
