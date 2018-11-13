Gem::Specification.new do |spec|
  spec.name          = "gemspec1"
  spec.version       = "0.0.0"
  spec.authors       = ["n/a"]
  spec.email         = ["n/a"]
  spec.description   = ""
  spec.summary       = ""
  spec.license       = "BSD"
  spec.files         = ['lib/gemspec1.rb', 'foo/bar/blah.json']
  spec.executables   = ['gemspec1']

  spec.add_dependency 'concurrent-ruby', '1.0.5'
end
