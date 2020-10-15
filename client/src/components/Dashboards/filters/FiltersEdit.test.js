/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {getVariableNames} from './service';

import FiltersEdit from './FiltersEdit';

const props = {
  availableFilters: [],
  setAvailableFilters: jest.fn(),
  reports: [{id: 'reportId'}],
};

jest.mock('./service', () => ({
  getVariableNames: jest.fn(),
}));

beforeEach(() => {
  props.setAvailableFilters.mockClear();
  getVariableNames.mockClear();
});

it('should show added filters', () => {
  const node = shallow(<FiltersEdit {...props} availableFilters={[{type: 'state'}]} />);

  expect(node.find('InstanceStateFilter')).toExist();
});

it('should allow removing existing filters', () => {
  const node = shallow(<FiltersEdit {...props} availableFilters={[{type: 'state'}]} />);

  node.find('InstanceStateFilter .deleteButton').simulate('click');

  expect(props.setAvailableFilters).toHaveBeenCalledWith([]);
});

it('should allow editing variable filters', () => {
  const node = shallow(
    <FiltersEdit {...props} availableFilters={[{type: 'variable', data: {name: 'varName'}}]} />
  );

  node.find('VariableFilter .editButton').simulate('click');

  expect(node.find('.dashboardVariableFilter')).toExist();
  expect(node.find('.dashboardVariableFilter').prop('filterData')).toEqual({
    type: 'variable',
    data: {name: 'varName'},
  });
});
