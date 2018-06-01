FROM java:openjdk-8-jre-alpine
MAINTAINER Francesco
ARG GIT_REVISION=unknown
LABEL git-revision=$GIT_REVISION    
COPY jars/serv_ass_mgr-bin-0.1.1.jar /home/serv_ass_mgr.jar
COPY config/basic.json /home/config/basic.json
WORKDIR /home
CMD ["java","-cp","/home/serv_ass_mgr.jar","fivegex.sla.ServiceAssuranceManager"]
