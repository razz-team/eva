FROM postgres:13.3

RUN apt-get update  \
	&& apt-get install -y git make gcc
RUN git clone https://github.com/pgpartman/pg_partman
RUN cd pg_partman  \
	&& make NO_BGW=1 install