FROM node:20-alpine
WORKDIR /app

# Kopiraj samo package fajlove prvo (kako bi Docker keširao npm install)
COPY package.json package-lock.json* ./
RUN npm install

# Ne radimo "COPY . ." niti "npm run build"!
# Celokupan kod ćemo mapirati preko volumes u docker-compose.yml

EXPOSE 4200
# Pokrećemo start skriptu iz package.json
CMD ["npm", "start"]