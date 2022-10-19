
### Description
```
Реализовать сервис, периодически опрашивающий определённый набор URL’ов на предмет доступности.
Управление сервисом (настройка интервала опроса, списка опрашиваемых URL) и получение результатов работы должны осуществляться посредством обращения к сервису с помощью HTTP-запросов.
Хранение результатов и набора url’ов можно реализовать inmemory, вместо бд.
Желательно упаковать приложение в docker контейнер
Также необходимо описать процедуру запуска/конфигурирования в README
Код и описание необходимо загрузить на github
Языки программирования: Java/Scala/Kotlin
Frameworks: spring, micronaut, ktor
Сборка проекта: Gradle kotlin script
```

### Requirements
 * jdk 17+
 * kotlin 1.7.10+
 * docker engine version 20.10.13+ (with running docker demon)
 * docker-compose 2.3.3+
 * tested at: gnu/linux, windows11

### How build and run

##### Using docker-compose
```shell
./gradlew bootBuildImage
docker-compose -f ./docker/docker-compose.yml up
```
##### Using gradlew only
```shell
./gradlew bootRun
```

### How check docker-compose setup
```shell
# setup new setting
curl -XPOST 'http://localhost:18080/setting' -H 'Content-Type: application/json' -d '{
  "urls": ["http://service1:80", "http://service2:80", "http://service3:80"],
  "intervalMs": 10000
}'
# get current setting
curl 'http://localhost:18080/setting' 
# get health
curl 'http://localhost:18080/healths'
```

### Problem with docker machine at windows11 wsl2 subsystem
There are some IO issue with nginx docker container under docker-machine win11 with wsl2 subsystem. Nginx container has a small chance to reject some http requests.
Guess it may be connected with this [issue](https://github.com/microsoft/WSL/issues/4197) 
To prevent it you may use a simple python3 file server running in special directory:
```sh
python3 -m http.server
```