-- Older prompts asked the model to append source titles to its answer, so
-- stored AI messages can end with text such as "(Source: Employee Handbook)".
-- Citations are rendered separately in the inbox and widget, so remove that
-- duplicate annotation from existing messages.
with cleaned as (
    select id,
           btrim(regexp_replace(body, '\s*\(Sources?:\s*[^)]*\)', '', 'gi')) as body
    from messages
    where author_type = 'AI'
      and body ~* '\(Sources?:\s*[^)]*\)'
)
update messages m
set body = c.body
from cleaned c
where m.id = c.id
  and c.body <> '';
