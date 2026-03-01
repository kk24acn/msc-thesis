## Docker compose commands

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
ethers.formatEther(await provider.getBalance(acc));
```
