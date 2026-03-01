FROM node:24-alpine

WORKDIR /app

COPY package.json package-lock.json* ./

RUN npm ci

COPY . .

EXPOSE 8545

ENTRYPOINT ["npx", "hardhat", "node"]

CMD ["--hostname", "0.0.0.0"]
