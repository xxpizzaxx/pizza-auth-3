pizza-auth-3
============

[![Travis](https://img.shields.io/travis/xxpizzaxx/pizza-auth-3.svg?style=flat-square)](https://travis-ci.org/xxpizzaxx/pizza-auth-3)
[![Codecov](https://img.shields.io/codecov/c/github/xxpizzaxx/pizza-auth-3.svg?style=flat-square)](https://codecov.io/github/xxpizzaxx/pizza-auth-3)
[![GitHub license](https://img.shields.io/github/license/xxpizzaxx/pizza-auth-3.svg?style=flat-square)](https://github.com/xxpizzaxx/pizza-auth-3/blob/master/LICENSE)

What is this?
=============

It's an alliance/corporation/group authorisation system, it includes an LDAP server, a graph database, and a durable event queue.

What does it do?
================

It manages the LDAP database for the group, maintaining accounts that can be used with other services using industry-standard plugins.

It allows you to send location-aware broadcasts, based on where people's alts are logged in, here's a screenshot from the Minimum Viable Product version:

![broadcast example from the minimum viable product version](https://raw.githubusercontent.com/xxpizzaxx/pizza-auth-3/master/mvp.PNG)

What are the caveats for the minimum viable product version?
============================================================

You may need to clear out the temporary databases between runs, it does not have all of the HTTP interface implemented yet, and it does not have Broadcast interfaces implemented.

How do I use the MVP version?
=============================

`sbt test` to test it, see the test runs on travis [here](https://travis-ci.org/xxpizzaxx/pizza-auth-3/builds)

to run it, run `moe.pizza.auth.Main` in an IDE with the argument `server` and provide it a valid `config.yml` with CREST variables filled in