-- Anzahl der ersten Runden, die komplett zufällig ausgelost werden (Standard 2)
alter table tournament add column random_rounds integer not null default 2;
