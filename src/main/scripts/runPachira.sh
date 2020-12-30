#!/bin/bash

# check if java exists
if ! type -p java > /dev/null
then
  echo java not found. abort
  exit 1
fi

# check java version
version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
major=$(echo "$version" | cut -d. -f1)
if [[ $major -lt 11 ]]
then
  echo cannot run with java version older than 11.  abort
  exit 1
fi

pDir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
java --module-path="${PATH_TO_FX:-/usr/share/openjfx/lib}" \
 --add-exports javafx.base/com.sun.javafx.event=ALL-UNNAMED \
 --add-modules=javafx.fxml,javafx.controls \
 -jar "${pDir}"/Pachira.jar