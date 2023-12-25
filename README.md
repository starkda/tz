это проект по тз:
https://docs.google.com/document/d/1wW70asO6XmOLXWlnTDraFwZLavGuoIAHvppBAD6Pke4/edit

Обход ограничений:
апишка не позволяет себя дергать чаще, чем 30 раз в минуту с одного ключа. Выход - использовать несколько
api. Я это реализовал след. образом:
У меня есть пулл потоков в который посылаются задачи вида: дерни api с текущим акутальным ключом.
Если:
вернулось 200 - обрабатываем и отправляем данные в эластик
вернулось 429 - сдвигаем индекс текущего ключа. Этот индекс общий для всех потоков. Синхронизацию я решил сделав ee volatile AtomicInteger.

Можно заметить что в общем случае мы дойдем лимита по ключу в n раз быстрее(где n кол-во ключей).
Но и самих ключей будет столько же, получается что и Requests Per Second ускорится в N раз(без учета сети). Ну и очевидно что таким образом дернуть больше 30 за раз не получится.

Альтернативный подход это как-то распределить имеющиеся ключи над имеющимися потоками и он как будто не имеет верхней границы. 
Но плюсы моей реализации в простоте :)

На картинках будет показано как работают запросы(chema.png)

Пока стресс-тестировал на 2х ключах я получил прирост RPS ~ 1.7 раз по сравнению с 1 ключом.
Запуск проекта:

Для запуска нужно запустить следующую докер-команду. При запуске может быть довольно долго кидаться ошибка. Это из-за того что эластик долго поднимается.

mvn clean package

docker-compose down && docker-compose build --no-cache && docker-compose up -d && docker attach currency

Для тестов достаточно:
mvn test.