#!/bin/bash

cat /etc/nginx/conf.d/default.conf.templ \
  | sed s:API_HOST:${API_HOST:-api}:     \
  | sed s:API_PORT:${API_PORT:-8080}:    \
  | sed s:UI_PORT:${UI_PORT:-80}:        \
  | sed s:UI_HOST:${UI_HOST:-localhost}: \
> /etc/nginx/conf.d/default.conf
