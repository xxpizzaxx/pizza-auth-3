# Webapp Specification

## GET /
show landing page, swap based on if the user's signed in or not

## GET /services
list of services that are linked to this

## GET /groups
list of their groups, list of ones they can apply to or join

### POST /groups/apply/{name}

apply to a group

### POST /groups/remove/{name}
leave group

## GET /account
show account details and allow management of API keys and CREST keys

### POST /account/crest/add
redirect to a CREST callback to add a new CREST key

### POST /account/crest/remove/{id}
remove a given CREST identity

### POST /account/crest/primary/{id}
pick a new primary CREST key and rename your account

### POST /account/update
update your email or password

