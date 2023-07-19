/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode, useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {FilterableMultiSelect} from '@carbon/react';

import {t} from 'translation';
import debouncePromise from 'debouncePromise';
import {formatters, getRandomId} from 'services';

import {searchIdentities, User} from './service';

import './MultiUserInput.scss';

const debounceRequest = debouncePromise();

export interface MultiUserInputProps {
  titleText?: ReactNode;
  users: User[];
  collectionUsers?: User[];
  onAdd: (value: {id: string} | User['identity']) => void;
  fetchUsers?: (
    query: string,
    excludeGroups?: boolean
  ) => Promise<{total: number; result: User['identity'][]}>;
  optionsOnly?: boolean;
  onRemove: (id: string) => void;
  onClear: () => void;
  excludeGroups?: boolean;
  persistMenu?: boolean;
}

type Item = {
  id: string;
  label: string;
  tag?: string | null;
  subTexts?: (string | null)[];
  disabled?: boolean;
};

export default function MultiUserInput({
  users = [],
  collectionUsers = [],
  onAdd,
  fetchUsers,
  optionsOnly,
  onRemove,
  onClear,
  excludeGroups = false,
  persistMenu,
  titleText,
}: MultiUserInputProps): JSX.Element {
  const [loading, setLoading] = useState(true);
  const [identities, setIdentities] = useState<User['identity'][]>([]);
  const [textValue, setTextValue] = useState('');
  const multiSelectRef = useRef<HTMLElement>(null);
  const selectedUsers = useMemo(() => users.map((user) => formatIdentity(user.identity)), [users]);

  const loadNewValues = useCallback(
    async (query: string, delay = 0) => {
      setLoading(true);

      const {result} = await debounceRequest(async () => {
        return await (fetchUsers || searchIdentities)(query, excludeGroups);
      }, delay);

      setIdentities(result);
      setLoading(false);
    },
    [fetchUsers, excludeGroups]
  );

  useEffect(() => {
    loadNewValues('');

    function handleInputChange(evt: KeyboardEvent) {
      const excludedKeys = [' ', 'Enter', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'];

      if (!excludedKeys.includes(evt.key)) {
        const text = (evt?.target as HTMLInputElement)?.value;
        setTextValue(text);
        loadNewValues(text, 800);
      }
    }

    function handleBlur() {
      setTextValue('');
      loadNewValues('');
    }

    const input = multiSelectRef.current?.querySelector('input');

    if (input) {
      input.addEventListener('keyup', handleInputChange);
      input.addEventListener('blur', handleBlur);
    }

    return () => {
      if (input) {
        input.removeEventListener('keyup', handleInputChange);
        input.removeEventListener('blur', handleBlur);
      }
    };
  }, [loadNewValues]);

  function addIdentity(id: string) {
    if (id || id === null) {
      const selectedIdentity = identities
        .filter(filterSelected)
        .find((identity) => getUserId(identity) === id);
      if (selectedIdentity) {
        onAdd(selectedIdentity);
      } else {
        onAdd({id});
      }
    }
  }

  const filterSelected = ({id, type}: User['identity']) => {
    const exists = (users: User[]) =>
      users.some((user) => user.id === `${type.toUpperCase()}:${id}`);

    return !exists(users) && !exists(collectionUsers);
  };

  function getItems(): Item[] {
    if (loading) {
      return [{id: 'loading', label: textValue, disabled: true}];
    }

    const items = identities.filter(filterSelected).map(formatIdentity);
    items.unshift(...selectedUsers);

    if (!optionsOnly && textValue && !identities.some((item) => item.id === textValue)) {
      items.unshift({id: textValue, label: textValue});
    }

    return items;
  }

  return (
    <FilterableMultiSelect
      titleText={titleText}
      id={getRandomId()}
      className="MultiUserInput"
      placeholder={t('common.collection.addUserModal.searchPlaceholder')}
      ref={multiSelectRef}
      // disable the internal sorting since we have the data sorted by default
      sortItems={(items) => items}
      initialSelectedItems={selectedUsers}
      downshiftProps={{
        onSelect: (item) => {
          if (!item) {
            return;
          }

          const userToRemove = users.find((user) => user.id === item.id);

          if (userToRemove) {
            onRemove(userToRemove.id);
          } else {
            addIdentity(item.id);
            if (persistMenu === false) {
              multiSelectRef.current?.querySelector<HTMLElement>('[data-toggle="true"]')?.click();
            }
          }
        },
      }}
      onChange={({selectedItems}) => {
        if (selectedItems.length === 0) {
          onClear();
        }
      }}
      items={getItems()}
      itemToString={(item) => {
        const {label, tag, subTexts} = item;
        return label + (tag || '') + subTexts?.filter((subText) => !!subText).join(',');
      }}
      itemToElement={(item) => {
        if (item.id === 'loading') {
          return <p className="cds--checkbox-label-text cds--skeleton" />;
        }

        const {label, tag, subTexts} = item;
        return (
          <>
            {formatters.getHighlightedText(label, textValue)}
            {tag}
            {subTexts && (
              <span className="subTexts">
                {subTexts
                  .filter((subText): subText is string => !!subText)
                  .map((subText, i) => (
                    <span className="subText" key={i}>
                      {formatters.getHighlightedText(subText, textValue, true)}
                    </span>
                  ))}
              </span>
            )}
          </>
        );
      }}
    />
  );
}

function formatTypeaheadOption({name, email, id, type}: User['identity']): {
  label: string;
  tag: string | null;
  subTexts: (string | null)[];
} {
  const subTexts: (string | null)[] = [];
  if (name && email) {
    subTexts.push(email);
  }

  if (name || email) {
    subTexts.push(id);
  }

  return {
    label: name || email || id || '',
    tag: type === 'group' ? ` (${t('common.user-group.label')})` : null,
    subTexts,
  };
}

function formatIdentity(identity: User['identity']): Item {
  const {label, tag, subTexts} = formatTypeaheadOption(identity);

  return {
    id: getUserId(identity),
    label,
    tag,
    subTexts,
  };
}

function getUserId(identity: User['identity']) {
  return `${identity.type.toUpperCase()}:${identity.id}`;
}
