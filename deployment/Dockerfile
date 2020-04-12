FROM alpine:latest

ARG JDK=""
ARG APP=""
COPY ${JDK} /opt/jdk/
COPY ${APP} /opt/app/
WORKDIR /opt/app

ENV JAVA_OPTS="-Xmx512m"
ENV APP=${APP}
ENV JAVA_HOME=/opt/jdk
ENV PATH=$PATH:$JAVA_HOME/bin

RUN echo "java $JAVA_OPTS -jar $APP  \$@" > /opt/app.sh

ENTRYPOINT ["/bin/ash", "/opt/app.sh"]


