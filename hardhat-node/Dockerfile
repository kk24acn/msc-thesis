FROM node:24-alpine

WORKDIR /app

COPY package*.json ./

ENV NODE_ENV=development

RUN npm ci

COPY . .

EXPOSE 8545

CMD ["npx", "hardhat", "node", "--hostname", "0.0.0.0"]