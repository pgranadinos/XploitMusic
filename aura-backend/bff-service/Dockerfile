FROM node:20-alpine

WORKDIR /usr/src/app

# Instalar Python y yt-dlp (Requerido para el scraping de audio)
RUN apk add --no-cache python3 ffmpeg \
    && wget https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -O /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp

COPY package*.json ./

RUN npm install

COPY . .

EXPOSE 8080

CMD ["node", "index.js"]
