##  Cách khởi động PostgreSQL.
docker start <name_container>
## Port.
 5433:5432
## Database name.
atlasflow
## Username.
atlasflow
## Cách dừng database.
docker stop atlasflow
## Cách xem log.
docker logs <container_name>
## Cách kiểm tra kết nối.
docker ps