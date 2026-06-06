## Docker compose commands

### Full restart
```sh
docker compose -f docker-compose.all.yml down
docker compose -f docker-compose.all.yml up -d --build
```

### Full restart + data cleanup
```sh
docker compose -f docker-compose.all.yml down
docker volume prune -f --all
docker compose -f docker-compose.all.yml up -d --build
```

### Start docker compose
```sh
docker compose up
```

### Enter docker container (`hardhat`) with Bash
```sh
docker compose exec hardhat sh
```

### Enter hardhat console (in shell with running node)
```sh
npx hardhat console --network localhost
```
```sh
var signers = await ethers.getSigners();
var acc = signers[0].address;
ethers.formatEther(await ethers.provider.getBalance(acc));
```


```sh
let hre = await network.connect();
await hre.ethers.getSigners()
let signer = (await hre.ethers.getSigners())[0];
let wei = await signer.provider.getBalance(signer.address);
hre.ethers.formatEther(wei);
```


```sh
let signers = await hre.ethers.getSigners();

for (let i = 0; i < signers.length; i++) {
  let signer = signers[i];
  let address = signer.address;

  let balanceWei = await signer.provider.getBalance(address);
  let balanceEth = hre.ethers.formatEther(balanceWei);

  console.log(`Account ${i}: ${address} — ${balanceEth} ETH`);
}
```

## Third-Party Software and Licensing
This project leverages the following open-source technologies:
- Spring Boot 4 (Apache License 2.0)
- Hardhat 3 (MIT License)
- React 19 (MIT License)
- PostgreSQL 18 (PostgreSQL License)

The isolated signing nodes utilize the `dkls23-ll` library from Silence Laboratories. This components is governed strictly by the Silence Laboratories Non-Commercial Use License Agreement preserved in the LICENSE and NOTICE files of this repository.