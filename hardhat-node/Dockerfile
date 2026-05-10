FROM node:24-alpine

WORKDIR /app

COPY package*.json ./

ENV NODE_ENV=development

RUN npm ci

COPY . .

EXPOSE 8545

CMD ["node", "./node_modules/.bin/hardhat", "node", "--network", "hardhatMainnet", "--hostname", "0.0.0.0"]