#!/bin/sh
# Site configuration properties (environment variables)
# For an overview on the use of these files see mds wiki:
# https://github.com/richardrodgers/mds/wiki/Runtime-Injectable-Configuration
#
# Syntax rules:
# - use upper case, underscored property (variable) names to conform with environment variable standard practice
# - double quote value strings that contain whitespace or any special characters
# - refrain from whitespace between names and values and the equals sign: causes errors when Docker parses file
# - to override a kernel property named 'x.y', define an environment variable 'MDS_X_Y'
# - to override a property named 'x.y' in module 'z', define an environment variable 'MDS_MOD_Z_X_Y' 

# Site name
MDS_SITE_NAME="MDS at Modern Repo"
export MDS_SITE_NAME

# Base directory of deployment - may be managed by container system
# Uncomment and assign for local deployment
MDS_SITE_HOME=/mds/rt
export MDS_SITE_HOME

# DB Connection parameters - define only if *not* deployed in a Docker container
#DB_PORT_5432_TCP_ADDR=localhost
#export DB_PORT_5432_TCP_ADDR
#DB_PORT_5432_TCP_PORT=5432
#export DB_PORT_5432_TCP_PORT

# Site administrator
MDS_ADMIN_EMAIL=admin@mysite.edu
export MDS_ADMIN_EMAIL
MDS_ADMIN_FIRST=Ima
export MDS_ADMIN_FIRST
MDS_ADMIN_LAST=Admin
export MDS_ADMIN_LAST
MDS_ADMIN_PASSWORD=admin
export MDS_ADMIN_PASSWORD
