use clap::Parser;
use redb::{ Database, ReadableDatabase, ReadableTable, TableDefinition };

const KEYSHARES_TABLE: TableDefinition<&str, &[u8]> = TableDefinition::new("keyshares");

#[derive(Parser)]
struct Args {
    db_path: String,
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args = Args::parse();
    let db = Database::create(&args.db_path)?;
    let read_txn = db.begin_read()?;
    let table = read_txn.open_table(KEYSHARES_TABLE)?;

    println!("--- Reading redb keyshares ---");
    for item in table.iter()? {
        let (key, value) = item?;
        println!("Key: {:<20} | Data Size: {} bytes", key.value(), value.value().len());
    }

    Ok(())
}
