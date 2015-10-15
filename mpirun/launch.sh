#!/bin/bash -e

#SBATCH -p short
#SBATCH -n1
#SBATCH -C alpha
#SBATCH -D /scratch/lustre/home/mama9951/scalaTest

# Dir vars
dir=~/scalaTest
MPJ_HOME=~/mpj-v0_44-java6compatible

# Env vars
PATH=${MPJ_HOME}:$PATH
# Let subshells know the config:
export MPJ_HOME
export PATH

# Java args
jarDir="${dir}/scalaMpjTest/target/scala-2.11"
jarTitle='Fractals.jar'
jar=${jarDir}/${jarTitle}
lib=${MPJ_HOME}/lib

mpirun java -Djava.library.path=${lib} -jar ${jar} 0 0 native
