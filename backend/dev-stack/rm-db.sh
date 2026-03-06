#!/bin/bash
PROJECT="backend"
docker compose -p $PROJECT stop postgres
docker compose -p $PROJECT rm -f postgres
docker volume rm ${PROJECT}_postgres_data
docker compose -p $PROJECT up -d
